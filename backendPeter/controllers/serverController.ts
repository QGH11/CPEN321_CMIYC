import { Request, Response, NextFunction } from "express";
import { client } from "../services";
import { messaging } from "../services";
import { ObjectId } from "mongodb";
import os from 'os';


export class ServerController {
    async setFCMToken(req: Request, res: Response, nextFunction: NextFunction) {
        const fcmToken = req.body.fcmToken;
        const name = req.body.name;
        const collection = client.db("test").collection("names");
        
        // Try to update existing document first
        const result = await collection.updateOne(
            { name },
            { $set: { fcmToken } },
            { upsert: true }
        );

        res.status(200).send({
            message: "FCM token set successfully",
            isNewDocument: result.upsertedCount === 1
        });
    }

    async getFCMToken(req: Request, res: Response, nextFunction: NextFunction) {
        const name = req.body.name;
        const collection = client.db("test").collection("names");
        const fcmToken = await collection.findOne({ name });
        res.status(200).send({
            fcmToken: fcmToken?.fcmToken,
        });
    }

    async updateLocation(req: Request, res: Response, nextFunction: NextFunction) {
        const name = req.body.name;
        const location = req.body.location;
        const collection = client.db("test").collection("names");
        const result = await collection.updateOne({ name }, { $set: { location } });
        res.status(200).send({ message: "Location updated successfully" });
    }

    async sendEventNotification(req: Request, res: Response, nextFunction: NextFunction) {
        const name = req.body.name;
        const collection = client.db("test").collection("names");
        
        try {
            const user = await collection.findOne({ name });
            
            if (!user) {
                return res.status(404).send({ message: "User not found" });
            }

            if (!user.location) {
                return res.status(400).send({ message: "User location not set" });
            }

            // Get all friends' documents
            const friends = await collection.find({ 
                name: { $in: user.friends || [] }
            }).toArray();

            // Calculate distances
            const nearbyFriends = friends.filter((friend) => {
                if (!friend.location) return false;
                
                const distance = ServerController.calculateDistance(
                    user.location.lat,
                    user.location.lng,
                    friend.location.lat,
                    friend.location.lng
                );
                
                return distance <= 1; // 1 kilometer
            });

            // Send notifications to nearby friends
            const notificationPromises = nearbyFriends.map(friend => {
                if (!friend.fcmToken) return Promise.resolve();
                
                return messaging.send({
                    token: friend.fcmToken,
                    notification: {
                        title: "New Event",
                        body: `${req.body.name} is starting a new event!`
                    }
                });
            });

            await Promise.all(notificationPromises);

            res.status(200).send({
                nearbyFriends: nearbyFriends.map((f) => ({
                    name: f.name,
                    distance: ServerController.calculateDistance(
                        user.location.lat,
                        user.location.lng,
                        f.location.lat,
                        f.location.lng
                    ),
                    fcmToken: f.fcmToken
                })),
                message: "Nearby friends retrieved successfully"
            });
            
        } catch (error) {
            console.error(error);
            res.status(500).send({ error: (error as Error).message });
        }
    }

    private static toRadians(degrees: number): number {
        return degrees * (Math.PI/180);
    }

    private static calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
        const R = 6371; // Earth radius in kilometers
        const dLat = ServerController.toRadians(lat2 - lat1);
        const dLon = ServerController.toRadians(lon2 - lon1);
        
        const a = 
            Math.sin(dLat/2) * Math.sin(dLat/2) +
            Math.cos(ServerController.toRadians(lat1)) * Math.cos(ServerController.toRadians(lat2)) *
            Math.sin(dLon/2) * Math.sin(dLon/2);
            
        const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
        return R * c;
    }
}
